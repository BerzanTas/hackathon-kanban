import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/utils'
import { BoardPage } from './BoardPage'

test('renders the five workflow columns', async () => {
  renderWithProviders(<BoardPage />)
  expect(await screen.findByText('Fix login bug')).toBeInTheDocument()

  for (const label of [
    'New',
    'Ready for Implementation',
    'In Progress',
    'Ready for Acceptance',
    'Done',
  ]) {
    expect(screen.getByRole('region', { name: label })).toBeInTheDocument()
  }
})

test('groups tickets into their state columns', async () => {
  renderWithProviders(<BoardPage />)
  await screen.findByText('Fix login bug')

  const newCol = screen.getByRole('region', { name: 'New' })
  const doneCol = screen.getByRole('region', { name: 'Done' })
  expect(within(newCol).getByText('Fix login bug')).toBeInTheDocument()
  expect(within(doneCol).getByText('Refactor API')).toBeInTheDocument()
})

test('filtering by type narrows the visible cards', async () => {
  renderWithProviders(<BoardPage />)
  await screen.findByText('Fix login bug')

  fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'bug' } })

  expect(screen.getByText('Fix login bug')).toBeInTheDocument()
  expect(screen.queryByText('Add dark mode')).not.toBeInTheDocument()
  expect(screen.queryByText('Refactor API')).not.toBeInTheDocument()
})

test('title search filters case-insensitively', async () => {
  renderWithProviders(<BoardPage />)
  await screen.findByText('Fix login bug')

  fireEvent.change(screen.getByLabelText('Search'), {
    target: { value: 'dark' },
  })

  expect(screen.getByText('Add dark mode')).toBeInTheDocument()
  expect(screen.queryByText('Fix login bug')).not.toBeInTheDocument()
})

test('shows an empty state when there are no teams', async () => {
  server.use(http.get('/teams', () => HttpResponse.json([])))
  renderWithProviders(<BoardPage />)

  await waitFor(() =>
    expect(screen.getByText('No teams yet')).toBeInTheDocument(),
  )
})
